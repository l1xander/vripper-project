export class PostDetails {
  constructor(
    public postId: string,
    public url: string,
    public progress: number,
    public status: string,
    public index: number
  ) {}
}
